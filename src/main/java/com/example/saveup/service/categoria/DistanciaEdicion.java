package com.example.saveup.service.categoria;

/**
 * Distancia de Damerau-Levenshtein (variante de alineación óptima de cadenas),
 * con un tope para cortar el cálculo. Sin dependencias de Spring.
 */
public final class DistanciaEdicion {

    private DistanciaEdicion() {
    }

    /**
     * Distancia entre {@code a} y {@code b}. Si supera {@code maximo}, devuelve
     * {@code maximo + 1} (el valor exacto no importa para las comparaciones).
     */
    public static int distancia(String a, String b, int maximo) {
        String izquierda = a == null ? "" : a;
        String derecha = b == null ? "" : b;
        int n = izquierda.length();
        int m = derecha.length();

        if (Math.abs(n - m) > maximo) {
            return maximo + 1;
        }
        if (n == 0) {
            return m > maximo ? maximo + 1 : m;
        }
        if (m == 0) {
            return n > maximo ? maximo + 1 : n;
        }

        int[][] matriz = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            matriz[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            matriz[0][j] = j;
        }

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int costoSustitucion = izquierda.charAt(i - 1) == derecha.charAt(j - 1) ? 0 : 1;
                int valor = Math.min(
                        Math.min(matriz[i - 1][j] + 1, matriz[i][j - 1] + 1),
                        matriz[i - 1][j - 1] + costoSustitucion);

                boolean transposicion = i > 1 && j > 1
                        && izquierda.charAt(i - 1) == derecha.charAt(j - 2)
                        && izquierda.charAt(i - 2) == derecha.charAt(j - 1);
                if (transposicion) {
                    valor = Math.min(valor, matriz[i - 2][j - 2] + 1);
                }
                matriz[i][j] = valor;
            }
        }

        return matriz[n][m] > maximo ? maximo + 1 : matriz[n][m];
    }
}
