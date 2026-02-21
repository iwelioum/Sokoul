import { writable, derived } from 'svelte/store';
import { connectWebSocket, listDownloads, type Task, type WsEvent } from '../api/client';

// Map of tasks by ID
const tasksStore = writable<Record<string, Task>>({});

export const downloads = derived(tasksStore, ($tasks) => {
    return Object.values($tasks)
        .filter(t => t.task_type === 'download')
        .sort((a, b) => new Date(b.created_at || 0).getTime() - new Date(a.created_at || 0).getTime());
});

let ws: WebSocket | null = null;

export function initDownloadsStore() {
    // 1. Initial fetch
    fetchDownloads();

    // 2. Connect WS if not already connected
    if (!ws || ws.readyState === WebSocket.CLOSED) {
        ws = connectWebSocket((event: WsEvent) => {
            handleWsEvent(event);
        });
    }
}

async function fetchDownloads() {
    try {
        const tasks = await listDownloads();
        const taskMap: Record<string, Task> = {};
        for (const t of tasks) {
            taskMap[t.id] = t;
        }
        tasksStore.set(taskMap);
    } catch (e) {
        console.error("Failed to fetch downloads:", e);
    }
}

function handleWsEvent(event: WsEvent) {
    if (event.type === 'download_progress') {
        const { task_id, media_id, progress } = event as any;
        tasksStore.update(tasks => {
            // Prefer matching by task_id for precision, fall back to media_id
            let targetId: string | undefined;
            if (task_id) {
                targetId = Object.keys(tasks).find(id => id === task_id);
            }
            if (!targetId && media_id) {
                targetId = Object.keys(tasks).find(id => {
                    const p = tasks[id].payload as any;
                    return p?.media_id === media_id && tasks[id].status === 'running';
                });
            }
            
            if (targetId) {
                return {
                    ...tasks,
                    [targetId]: { ...tasks[targetId], progress: progress }
                };
            }
            return tasks;
        });
    } else if (event.type === 'download_started' || event.type === 'download_completed' || event.type === 'download_failed') {
        // Refresh full list to get correct status/IDs
        fetchDownloads();
    }
}
