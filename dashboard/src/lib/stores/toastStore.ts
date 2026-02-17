import { writable } from 'svelte/store';

	export interface Toast {
		id: string;
		message: string;
		type: 'success' | 'error' | 'warning' | 'info';
		duration?: number;
	}

	export const toasts = writable<Toast[]>([]);

	export function addToast(
		message: string,
		type: 'success' | 'error' | 'warning' | 'info' = 'info',
		duration: number = 3000
	) {
		const id = Math.random().toString(36).substr(2, 9);
		const toast: Toast = { id, message, type, duration };

		toasts.update((t) => [...t, toast]);

		if (duration > 0) {
			setTimeout(() => removeToast(id), duration);
		}

		return id;
	}

	export function removeToast(id: string) {
		toasts.update((t) => t.filter((toast) => toast.id !== id));
	}

	export function success(message: string) {
		return addToast(message, 'success', 3000);
	}

	export function error(message: string) {
		return addToast(message, 'error', 5000);
	}

	export function warning(message: string) {
		return addToast(message, 'warning', 4000);
	}

	export function info(message: string) {
		return addToast(message, 'info', 3000);
	}