/**
 * Utilidades de imagen para el catálogo. Convierte un archivo elegido por el usuario a una
 * imagen cuadrada pequeña codificada como data URL (base64 JPEG), para guardarla junto al
 * producto sin necesitar un servicio de almacenamiento externo.
 */

/** Tamaño máximo del lado de la miniatura guardada (px). */
const MAX_SIZE = 256;

/**
 * Lee un archivo de imagen, lo recorta a un cuadrado centrado y lo reduce a {@link MAX_SIZE},
 * devolviendo un data URL JPEG. Rechaza si el archivo no es una imagen.
 *
 * @param file archivo elegido en un input type=file
 * @returns data URL (por ejemplo {@code data:image/jpeg;base64,...})
 */
export function fileToThumbnailDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    if (!file.type.startsWith('image/')) {
      reject(new Error('El archivo no es una imagen.'));
      return;
    }
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('No se pudo leer el archivo.'));
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error('No se pudo cargar la imagen.'));
      img.onload = () => {
        // Recorte cuadrado centrado.
        const side = Math.min(img.width, img.height);
        const sx = (img.width - side) / 2;
        const sy = (img.height - side) / 2;

        const canvas = document.createElement('canvas');
        canvas.width = MAX_SIZE;
        canvas.height = MAX_SIZE;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          reject(new Error('No se pudo procesar la imagen.'));
          return;
        }
        ctx.drawImage(img, sx, sy, side, side, 0, 0, MAX_SIZE, MAX_SIZE);
        resolve(canvas.toDataURL('image/jpeg', 0.82));
      };
      img.src = reader.result as string;
    };
    reader.readAsDataURL(file);
  });
}
