'use strict';

/**
 * Custom Leaflet TileLayer that batches requests to the backend
 * to avoid flooding the server with individual tile requests.
 */
L.TileLayer.Batch = L.TileLayer.extend({
    options: {
        batch_delay: 300,
        max_batch_size: 2000,
        batch_endpoint: '/tiles'
    },

    initialize: function (url_template, options) {
        L.TileLayer.prototype.initialize.call(this, url_template, options);
        this._pending_tiles = new Map();
        this._batch_timer = null;
        this._empty_tile_url = null;
        this._world_name = 'world';
        this._is_sending = false;
        this._queued_while_sending = new Map();
    },

    set_world: function (world_name) {
        this._world_name = world_name;
    },

    getTileUrl: function (coords) {
        return `/tiles?world=${this._world_name}&zoom=0&x=${coords.x}&z=${coords.y}`;
    },

    createTile: function (coords, done) {
        const tile = document.createElement('img');
        tile.alt = '';
        tile.setAttribute('role', 'presentation');

        const key = `0/${coords.x}/${coords.y}`;
        this._queue_tile_request(key, coords, tile, done);

        return tile;
    },

    _queue_tile_request: function (key, coords, tile, done) {
        // If we're currently sending, queue for next batch
        const target_map = this._is_sending ? this._queued_while_sending : this._pending_tiles;

        target_map.set(key, {
            tile: tile,
            done: done,
            coords: coords
        });

        if (this._batch_timer) {
            clearTimeout(this._batch_timer);
        }

        // Only auto-send if not currently sending and we hit a huge limit
        if (!this._is_sending && this._pending_tiles.size >= this.options.max_batch_size) {
            this._send_batch();
        } else if (!this._is_sending) {
            this._batch_timer = setTimeout(() => this._send_batch(), this.options.batch_delay);
        }
    },

    _send_batch: function () {
        if (this._pending_tiles.size === 0) return;

        this._is_sending = true;
        const all_tiles = new Map(this._pending_tiles);
        this._pending_tiles.clear();
        this._batch_timer = null;

        // Split into chunks of 200 tiles max
        const CHUNK_SIZE = 200;
        const chunks = [];
        let current_chunk = new Map();

        for (const [key, value] of all_tiles) {
            current_chunk.set(key, value);
            if (current_chunk.size >= CHUNK_SIZE) {
                chunks.push(current_chunk);
                current_chunk = new Map();
            }
        }
        if (current_chunk.size > 0) {
            chunks.push(current_chunk);
        }

        console.log(`Sending ${all_tiles.size} tiles in ${chunks.length} batch(es)`);

        // Send all chunks in parallel
        const chunk_promises = chunks.map(chunk => this._send_chunk(chunk));

        Promise.all(chunk_promises).finally(() => {
            this._is_sending = false;
            // Process any tiles that were queued while we were sending
            if (this._queued_while_sending.size > 0) {
                for (const [key, value] of this._queued_while_sending) {
                    this._pending_tiles.set(key, value);
                }
                this._queued_while_sending.clear();
                // Schedule next batch
                this._batch_timer = setTimeout(() => this._send_batch(), this.options.batch_delay);
            }
        });
    },

    _send_chunk: async function (batch) {
        const tiles = [];
        for (const [key, request] of batch) {
            const [zoom, x, y] = key.split('/').map(Number);
            // Backend expects {zoom, x, z} where z corresponds to y in Leaflet coords
            tiles.push({ zoom, x, z: y });
        }

        const request_body = {
            world: this._world_name,
            tiles: tiles
        };

        try {
            const response = await fetch(this.options.batch_endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request_body)
            });

            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            const data = await response.json();

            for (const [key, tile_data] of Object.entries(data.tiles)) {
                const request = batch.get(key);
                if (!request) continue;

                if (tile_data.empty) {
                    this._set_empty_tile(request.tile, request.done);
                } else if (tile_data.data) {
                    request.tile.src = 'data:image/png;base64,' + tile_data.data;
                    request.tile.onload = () => request.done(null, request.tile);
                    request.tile.onerror = () => request.done(new Error('Image load failed'), request.tile);
                } else if (tile_data.error) {
                    request.done(new Error(tile_data.error), request.tile);
                }
            }
        } catch (error) {
            console.error('Batch chunk failed:', error);
            for (const [key, request] of batch) {
                request.done(error, request.tile);
            }
        }
    },

    _set_empty_tile: function (tile, done) {
        if (!this._empty_tile_url) {
            this._empty_tile_url = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';
        }
        tile.src = this._empty_tile_url;
        done(null, tile);
    }
});

L.tileLayer.batch = function (url_template, options) {
    return new L.TileLayer.Batch(url_template, options);
};

// -- Configuration --
// 1 tile = 1 chunk = 32 blocks
const CHUNK_SIZE = 32;
const TILE_SIZE = 256;
const SCALE = TILE_SIZE / CHUNK_SIZE;  // 8 - Leaflet units per block

// -- State --
let map = null;
let tile_layer = null;
let current_world = 'world';
let websocket = null;
let player_markers = {};
let player_data = {};
let reconnect_timer = null;
let player_list_collapsed = false;

// -- Initialization --

document.addEventListener('DOMContentLoaded', () => {
    init_map();
    load_worlds();
    connect_websocket();
    init_player_list_toggle();
    document.getElementById('world-select').addEventListener('change', on_world_change);

    // Refresh worlds list periodically
    setInterval(load_worlds, 30_000);
});

function init_map() {
    // Using CRS.Simple: 1 unit = 1 pixel at zoom 0
    // We want 1 unit = 1 block, so we need to scale tiles.
    // Set large bounds to allow zooming out.
    const world_bounds = L.latLngBounds(
        L.latLng(-100_000, -100_000),
        L.latLng(100_000, 100_000)
    );

    map = L.map('map', {
        crs: L.CRS.Simple,
        minZoom: -4,
        maxZoom: 4,
        zoomSnap: 0.5,
        zoomDelta: 0.5,
        maxBounds: world_bounds,
        maxBoundsViscosity: 1.0
    });

    // Start at origin
    map.setView([0, 0], 0);

    update_tile_layer();

    map.on('mousemove', function (e) {
        // Convert Leaflet coords to world coords (divide by scale factor)
        // Negate lat because Leaflet's Y/Lat increases 'up', but Z increases 'down' in Minecrft (usually N=-Z)
        // Actually here: Z is mapped to Y in leaflet.
        // x = lng / scale
        // z = -lat / scale
        const x = Math.round(e.latlng.lng / SCALE);
        const z = Math.round(-e.latlng.lat / SCALE);
        document.getElementById('coords-display').textContent = `X: ${x}, Z: ${z}`;
    });

    map.attributionControl.addAttribution('VoxelAtlas');
}

function update_tile_layer() {
    if (tile_layer) {
        map.removeLayer(tile_layer);
    }

    // Batch tile layer - reduces HTTP requests by batching multiple tiles per request
    tile_layer = L.tileLayer.batch('/tiles', {
        tileSize: TILE_SIZE,
        minNativeZoom: 0,
        maxNativeZoom: 0,
        minZoom: -4,
        maxZoom: 4,
        noWrap: true,
        bounds: [[-100_000, -100_000], [100_000, 100_000]],
        batch_delay: 300,
        max_batch_size: 2000,
        batch_endpoint: '/tiles'
    });

    tile_layer.set_world(current_world);
    tile_layer.addTo(map);
}

// -- Helpers --

/**
 * Convert world coords to LatLng
 */
function world_to_latlng(x, z) {
    // X -> lng, Z -> -lat (north is up, Z increases south)
    // Multiply by SCALE since Leaflet uses tile-pixel coords (256px per 32-block chunk)
    return L.latLng(-z * SCALE, x * SCALE);
}

async function load_worlds() {
    try {
        const response = await fetch('/worlds');
        if (!response.ok) throw new Error('Network response was not ok');

        const data = await response.json();
        const worlds = data.worlds;

        const select = document.getElementById('world-select');
        select.innerHTML = '';

        worlds.forEach(world => {
            const option = document.createElement('option');
            option.value = world.name;
            option.textContent = world.name;
            if (world.name === current_world) option.selected = true;
            select.appendChild(option);
        });

        if (worlds.length > 0 && !worlds.find(w => w.name === current_world)) {
            current_world = worlds[0].name;
            update_tile_layer();
        }
    } catch (e) {
        console.error('Failed to load worlds:', e);
    }
}

function on_world_change(e) {
    current_world = e.target.value;
    update_tile_layer();
    clear_player_markers();
    update_player_list();
}

// -- Player Markers --

function clear_player_markers() {
    Object.values(player_markers).forEach(m => map.removeLayer(m));
    player_markers = {};
    player_data = {};
}

function rad_to_deg(rad) {
    return rad * (180 / Math.PI);
}

function create_arrow_icon(yaw_radians) {
    // Yaw is in radians, convert to degrees
    const yaw_deg = rad_to_deg(yaw_radians);
    const rotation = yaw_deg + 180;

    return L.divIcon({
        className: 'player-marker',
        html: `<div class="player-arrow" style="transform: rotate(${rotation}deg);"></div>`,
        iconSize: [20, 20],
        iconAnchor: [10, 10]
    });
}

function update_arrow_rotation(marker, yaw_radians) {
    const el = marker.getElement();
    if (el) {
        const arrow = el.querySelector('.player-arrow');
        if (arrow) {
            const yaw_deg = rad_to_deg(yaw_radians);
            const rotation = yaw_deg + 180;
            arrow.style.transform = `rotate(${rotation}deg)`;
        }
    }
}

// -- WebSocket --

function connect_websocket() {
    const status_el = document.getElementById('connection-status');
    status_el.textContent = 'Connecting...';
    status_el.className = 'connecting';

    websocket = new WebSocket(`ws://${location.host}/ws/players`);

    websocket.onopen = () => {
        status_el.textContent = 'Connected';
        status_el.className = 'connected';
        if (reconnect_timer) {
            clearTimeout(reconnect_timer);
            reconnect_timer = null;
        }
        console.log("WebSocket connected to /ws/players");
    };

    websocket.onmessage = (e) => {
        try {
            const data = JSON.parse(e.data);

            if (data.type === 'player_positions') {
                const worlds_map = {};
                if (Array.isArray(data.data)) {
                    data.data.forEach(item => {
                        worlds_map[item.world] = item.players;
                    });
                }
                update_players(worlds_map);
            }
        } catch (err) {
            console.error("Error parsing WebSocket message:", err);
        }
    };

    websocket.onclose = () => {
        status_el.textContent = 'Disconnected';
        status_el.className = 'disconnected';
        console.log("WebSocket disconnected, retrying in 3s...");
        if (!reconnect_timer) {
            reconnect_timer = setTimeout(connect_websocket, 3000);
        }
    };

    websocket.onerror = (err) => {
        console.error("WebSocket error:", err);
    };
}

function update_players(worlds_data) {
    const players = worlds_data[current_world] || [];
    const seen = new Set();
    let count = 0;

    // Reset player data store for current view (but preserve refs if needed? No, easy to rebuild)
    // Actually we keep playerData to map uuid -> info
    player_data = {};

    players.forEach(p => {
        seen.add(p.uuid);
        count++;
        const pos = world_to_latlng(p.x, p.z);
        const yaw = p.yaw || 0;

        // Store player data
        player_data[p.uuid] = {
            name: p.name,
            uuid: p.uuid,
            x: Math.round(p.x),
            y: Math.round(p.y),
            z: Math.round(p.z),
            yaw: yaw
        };

        if (player_markers[p.uuid]) {
            player_markers[p.uuid].setLatLng(pos);
            update_arrow_rotation(player_markers[p.uuid], yaw);
        } else {
            const marker = L.marker(pos, {
                icon: create_arrow_icon(yaw)
            });
            marker.bindTooltip(p.name, {
                permanent: false,
                direction: 'top',
                offset: [0, -12],
                className: 'player-tooltip'
            });
            marker.addTo(map);
            player_markers[p.uuid] = marker;
        }
    });

    // Remove offline players
    Object.keys(player_markers).forEach(uuid => {
        if (!seen.has(uuid)) {
            map.removeLayer(player_markers[uuid]);
            delete player_markers[uuid];
        }
    });

    document.getElementById('player-count-display').textContent = `Players: ${count}`;
    update_player_list();
}

// -- Player List UI --

function update_player_list() {
    const list_el = document.getElementById('player-list');
    const players = Object.values(player_data);

    if (players.length === 0) {
        list_el.innerHTML = '<li class="player-list-empty">No players online</li>';
        return;
    }

    // Sort by name
    players.sort((a, b) => a.name.localeCompare(b.name));

    list_el.innerHTML = players.map(p => `
        <li data-uuid="${p.uuid}" onclick="window.focus_player('${p.uuid}')">
            <span class="player-icon"></span>
            <span class="player-name">${escape_html(p.name)}</span>
            <span class="player-coords">${p.x}, ${p.z}</span>
        </li>
    `).join('');
}

function escape_html(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Exposed to global scope for onclick handler in HTML string
window.focus_player = function (uuid) {
    const p = player_data[uuid];
    if (p) {
        const pos = world_to_latlng(p.x, p.z);
        map.setView(pos, 0);  // Zoom level 0 for good detail
    }
};

function init_player_list_toggle() {
    const toggle_btn = document.getElementById('player-list-toggle');
    const content = document.getElementById('player-list-content');

    if (toggle_btn && content) {
        toggle_btn.addEventListener('click', () => {
            player_list_collapsed = !player_list_collapsed;
            if (player_list_collapsed) {
                content.classList.add('collapsed');
                toggle_btn.textContent = '+';
            } else {
                content.classList.remove('collapsed');
                toggle_btn.textContent = '-';
            }
        });
    }
}
