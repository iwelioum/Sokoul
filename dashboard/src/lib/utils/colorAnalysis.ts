/**
 * Image Color Analysis Utilities
 * Extracts dominant colors and brightness from images
 */

/**
 * Calculate luminance of RGB values (standard formula)
 * Returns value 0-255 (0 = dark, 255 = bright)
 */
export function calculateLuminance(r: number, g: number, b: number): number {
	// Relative luminance formula for WCAG
	const [rs, gs, bs] = [r, g, b].map((val) => {
		val = val / 255;
		return val <= 0.03928 ? val / 12.92 : Math.pow((val + 0.055) / 1.055, 2.4);
	});

	const luminance = 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
	return luminance * 255;
}

/**
 * Get dominant color from image element or URL
 * Returns { r, g, b, luminance }
 */
export async function getDominantColor(
	imageSrc: string
): Promise<{ r: number; g: number; b: number; luminance: number } | null> {
	return new Promise((resolve) => {
		const img = new Image();
		img.crossOrigin = 'anonymous';
		img.onload = () => {
			const canvas = document.createElement('canvas');
			canvas.width = img.width;
			canvas.height = img.height;

			const ctx = canvas.getContext('2d');
			if (!ctx) {
				resolve(null);
				return;
			}

			ctx.drawImage(img, 0, 0);

			// Get bottom-center area (where text usually is)
			const imageData = ctx.getImageData(0, canvas.height * 0.6, canvas.width, canvas.height * 0.4);
			const data = imageData.data;

			let r = 0,
				g = 0,
				b = 0;
			let pixelCount = 0;

			// Sample every 4th pixel for performance
			for (let i = 0; i < data.length; i += 16) {
				r += data[i];
				g += data[i + 1];
				b += data[i + 2];
				pixelCount++;
			}

			r = Math.round(r / pixelCount);
			g = Math.round(g / pixelCount);
			b = Math.round(b / pixelCount);

			const luminance = calculateLuminance(r, g, b);

			resolve({ r, g, b, luminance });
		};
		img.onerror = () => resolve(null);
		img.src = imageSrc;
	});
}

/**
 * Determine if text should be light or dark based on luminance
 * Returns 'light' for dark images, 'dark' for bright images
 */
export function getTextColor(luminance: number): 'light' | 'dark' {
	// Threshold: 128 (midpoint)
	return luminance > 128 ? 'dark' : 'light';
}

/**
 * Get overlay opacity based on luminance
 * Returns 0-1 for lateral overlay opacity
 */
export function getOverlayOpacity(luminance: number): number {
	if (luminance < 80) return 0.15; // Very dark → subtle overlay
	if (luminance < 120) return 0.25; // Dark → medium overlay
	if (luminance < 160) return 0.35; // Medium → stronger overlay
	return 0.45; // Bright → maximum overlay
}

/**
 * Get gradient mask opacity based on luminance
 * Returns string for CSS filter
 */
export function getGradientMaskOpacity(luminance: number): number {
	if (luminance < 100) return 0.7;
	if (luminance < 150) return 0.8;
	return 0.9;
}
