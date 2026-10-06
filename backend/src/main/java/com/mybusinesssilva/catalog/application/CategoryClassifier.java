package com.mybusinesssilva.catalog.application;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Clasifica un producto de Open Food Facts / Open Products Facts en una de las categorías
 * de negocio en español que maneja el catálogo maestro, y le asigna su clave SAT
 * (c_ClaveProdServ) y unidad de medida (c_ClaveUnidad).
 *
 * <p>Las categorías de OFF llegan en inglés/francés/español mezclado y con múltiples valores
 * separados por coma (por ejemplo {@code "Colas, pt:bebidas cafeína"}). Este clasificador
 * normaliza el texto (minúsculas, sin acentos) y aplica reglas de palabras clave en orden de
 * especificidad para elegir la categoría más adecuada. Si nada coincide, cae en "Abarrotes".
 *
 * <p>El diseño evita falsos positivos poniendo primero las reglas más específicas (bebé,
 * mascotas, higiene, limpieza) antes que las genéricas (abarrotes), porque una crema corporal
 * no debe clasificarse como "lácteo" por contener la palabra "cream".
 */
public final class CategoryClassifier {

    /** Resultado de la clasificación: categoría en español, clave SAT y unidad SAT. */
    public record Classification(String category, String satProdServ, String satUnit) {}

    /** Una regla: si el texto normalizado contiene alguna de las palabras clave, aplica la categoría. */
    private record Rule(String category, String satProdServ, String satUnit, List<String> keywords) {}

    private static final String SAT_UNIT_PIECE = "H87";

    /**
     * Reglas ordenadas por especificidad (de más específica a más general). El primer match gana.
     * Las palabras clave están normalizadas (minúsculas, sin acentos) igual que el texto de entrada.
     */
    private static final List<Rule> RULES = List.of(
            // --- No alimentos: primero para que no los capturen reglas de comida ---
            new Rule("Bebé", "42280000", SAT_UNIT_PIECE, List.of(
                    "baby", "bebe", "infant", "panal", "diaper", "formula infantil", "papilla")),
            new Rule("Mascotas", "10121800", SAT_UNIT_PIECE, List.of(
                    "pet food", "cat food", "dog food", "mascota", "alimento para perro",
                    "alimento para gato", "croqueta")),
            new Rule("Higiene personal", "53131600", SAT_UNIT_PIECE, List.of(
                    "shampoo", "champu", "soap", "jabon de tocador", "toothpaste", "dental",
                    "deodorant", "desodorante", "shaving", "rasurar", "sanitary", "toalla femenina",
                    "body wash", "gel de ducha", "lotion", "locion", "acondicionador", "conditioner",
                    "cuidado personal", "higiene", "cosmetic", "cosmetico")),
            new Rule("Limpieza", "47131800", SAT_UNIT_PIECE, List.of(
                    "detergent", "detergente", "cleaner", "limpiador", "bleach", "cloro",
                    "dishwash", "lavavajilla", "fabric softener", "suavizante", "laundry",
                    "limpieza", "desinfectant", "desinfectante", "papel higienico", "toilet paper",
                    "servilleta", "napkin", "paper towel")),

            // --- Alimentos y bebidas específicas ---
            new Rule("Bebidas", "50200000", SAT_UNIT_PIECE, List.of(
                    "beverage", "bebida", "soda", "soft drink", "cola", "refresco", "juice",
                    "jugo", "nectar", "agua", "water", "tea", "energy drink", "sports drink",
                    "isotonic", "cerveza", "beer", "wine", "vino", "licor", "spirit")),
            new Rule("Café y bebidas calientes", "50202300", SAT_UNIT_PIECE, List.of(
                    "coffee", "cafe", "te ", "infusion", "chocolate en polvo", "cocoa powder",
                    "capuccino", "espresso")),
            new Rule("Lácteos", "50130000", SAT_UNIT_PIECE, List.of(
                    "milk", "leche", "cheese", "queso", "yogurt", "yoghurt", "yogur", "cream",
                    "crema", "butter", "mantequilla", "dairy", "lacteo", "egg", "huevo",
                    "condensed milk", "leche condensada", "evaporated")),
            new Rule("Dulces y confitería", "50192700", SAT_UNIT_PIECE, List.of(
                    "candy", "dulce", "chocolate", "confectionery", "confiteria", "gummy",
                    "gomita", "caramel", "caramelo", "paleta", "lollipop", "chewing gum",
                    "chicle", "marshmallow", "malvavisco", "bombon", "mazapan")),
            new Rule("Cereales y desayuno", "50210000", SAT_UNIT_PIECE, List.of(
                    "cereal", "corn flakes", "hojuela", "granola", "oat", "avena", "muesli",
                    "breakfast", "desayuno")),
            new Rule("Panadería", "50180000", SAT_UNIT_PIECE, List.of(
                    "bread", "pan ", "bakery", "panaderia", "cake", "pastel", "cookie",
                    "galleta", "biscuit", "cracker", "tortilla", "pasta", "noodle", "pastry",
                    "reposteria", "muffin", "donut", "dona")),
            new Rule("Botanas", "50161800", SAT_UNIT_PIECE, List.of(
                    "snack", "botana", "chips", "papas fritas", "crisps", "popcorn",
                    "palomitas", "nut", "cacahuate", "peanut", "frituras", "pretzel")),
            new Rule("Enlatados", "50190000", SAT_UNIT_PIECE, List.of(
                    "canned", "enlatado", "en conserva", "tuna", "atun", "sardine", "sardina",
                    "beans", "frijol", "salsa", "sauce", "puree", "pure", "chiles",
                    "vegetables in", "preserved")),
            new Rule("Abarrotes", "50170000", SAT_UNIT_PIECE, List.of(
                    "sugar", "azucar", "salt", "sal ", "flour", "harina", "rice", "arroz",
                    "oil", "aceite", "vinegar", "vinagre", "spice", "especia", "condiment",
                    "condimento", "seasoning", "sazonador", "mayonnaise", "mayonesa", "ketchup",
                    "mustard", "mostaza", "honey", "miel", "jam", "mermelada", "grocery")));

    /** Categoría de reserva cuando ninguna regla coincide. */
    private static final Rule FALLBACK =
            new Rule("Abarrotes", "50000000", SAT_UNIT_PIECE, List.of());

    private CategoryClassifier() {}

    /**
     * Clasifica a partir de las categorías crudas de OFF y, como refuerzo, el nombre del producto.
     *
     * @param rawCategories cadena de categorías de OFF (puede venir vacía o nula)
     * @param productName   nombre del producto, usado como señal adicional
     * @return la clasificación elegida (nunca nula; cae en Abarrotes por defecto)
     */
    public static Classification classify(String rawCategories, String productName) {
        String haystack = normalize((rawCategories == null ? "" : rawCategories) + " "
                + (productName == null ? "" : productName));

        for (Rule rule : RULES) {
            for (String kw : rule.keywords()) {
                if (haystack.contains(kw)) {
                    return new Classification(rule.category(), rule.satProdServ(), rule.satUnit());
                }
            }
        }
        return new Classification(FALLBACK.category(), FALLBACK.satProdServ(), FALLBACK.satUnit());
    }

    /**
     * Normaliza texto para comparación: pasa a minúsculas (Locale ROOT), quita acentos/diacríticos
     * y colapsa espacios. Así "Cafés" y "cafe" comparan igual.
     */
    static String normalize(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        String noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return noAccents.replaceAll("\\s+", " ").trim();
    }

    /**
     * Devuelve, para diagnóstico, el mapa de categoría destino -> clave SAT usada. Útil para
     * validar la cobertura del clasificador.
     */
    public static Map<String, String> categorySatMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Rule rule : RULES) {
            map.putIfAbsent(rule.category(), rule.satProdServ());
        }
        map.putIfAbsent(FALLBACK.category(), FALLBACK.satProdServ());
        return map;
    }
}
