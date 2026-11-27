package com.jazaldcorp.inveragrovendedores.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hash simple (SHA-256) para poder verificar la contraseña localmente cuando no hay conexión
 * (login offline). No reemplaza la autenticación real contra Odoo -- solo permite reabrir la
 * app sin internet si ya se inició sesión con éxito alguna vez en este dispositivo.
 */
public class PasswordHashUtil {

    public static String hash(String dni, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((dni + ":" + password).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            return null;
        }
    }
}
