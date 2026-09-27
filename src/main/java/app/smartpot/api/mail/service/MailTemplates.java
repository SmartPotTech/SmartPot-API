package app.smartpot.api.mail.service;

import org.springframework.web.util.HtmlUtils;

/**
 * Plantillas de correo con estilos en línea (los clientes de correo ignoran las hojas externas).
 */
final class MailTemplates {

    private static final String PRIMARY = "#067A52";
    private static final String DEEP = "#0B3D2B";
    private static final String BRAND = "#00B074";
    private static final String INK = "#17261F";
    private static final String MUTED = "#5B6B63";
    private static final String SURFACE = "#F2F7F4";

    private MailTemplates() {
    }

    record Content(String html, String text) {
    }

    static Content welcome(String name, String loginUrl) {
        String safeName = HtmlUtils.htmlEscape(name);
        String body = paragraph("Hola " + safeName + ",")
                + paragraph("Tu cuenta de SmartPot está lista. Crea tu primer cultivo, real o virtual, y recibe "
                + "recomendaciones del asistente de IA para mantenerlo en su rango ideal.")
                + button("Ir a SmartPot", loginUrl);
        String text = "Hola " + name + ",\n\nTu cuenta de SmartPot está lista. Ingresa en: " + loginUrl;
        return new Content(layout("Bienvenido a SmartPot", body), text);
    }

    static Content passwordReset(String name, String resetUrl, long minutes) {
        String safeName = HtmlUtils.htmlEscape(name);
        String body = paragraph("Hola " + safeName + ",")
                + paragraph("Recibimos una solicitud para restablecer tu contraseña. El enlace vence en "
                + minutes + " minutos y solo se puede usar una vez.")
                + button("Restablecer contraseña", resetUrl)
                + small("Si no pediste este cambio, ignora este correo: tu contraseña sigue igual.");
        String text = "Hola " + name + ",\n\nRestablece tu contraseña en: " + resetUrl
                + "\nEl enlace vence en " + minutes + " minutos. Si no pediste el cambio, ignora este correo.";
        return new Content(layout("Restablece tu contraseña", body), text);
    }

    private static String layout(String title, String body) {
        return "<!doctype html><html lang=\"es\"><body style=\"margin:0;background:" + SURFACE
                + ";font-family:Segoe UI,Arial,sans-serif;color:" + INK + "\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:24px 0\">"
                + "<tr><td align=\"center\"><table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background:#FFFFFF;border-radius:14px;overflow:hidden\">"
                + "<tr><td style=\"background:" + DEEP + ";padding:22px 28px;border-bottom:4px solid " + BRAND + "\">"
                + "<span style=\"color:#FFFFFF;font-size:20px;font-weight:700;letter-spacing:.3px\">Smart<span style=\"color:"
                + BRAND + "\">Pot</span></span></td></tr>"
                + "<tr><td style=\"padding:28px\"><h1 style=\"margin:0 0 16px;font-size:22px;color:" + PRIMARY + "\">"
                + HtmlUtils.htmlEscape(title) + "</h1>" + body + "</td></tr>"
                + "<tr><td style=\"padding:16px 28px;background:" + SURFACE + ";font-size:12px;color:" + MUTED + "\">"
                + "SmartPot · Monitoreo y automatización de cultivos hidropónicos</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private static String paragraph(String text) {
        return "<p style=\"margin:0 0 14px;font-size:15px;line-height:1.55\">" + text + "</p>";
    }

    private static String small(String text) {
        return "<p style=\"margin:18px 0 0;font-size:12px;color:" + MUTED + "\">" + text + "</p>";
    }

    private static String button(String label, String url) {
        String safeUrl = HtmlUtils.htmlEscape(url);
        return "<p style=\"margin:22px 0\"><a href=\"" + safeUrl + "\" style=\"background:" + PRIMARY
                + ";color:#FFFFFF;text-decoration:none;padding:12px 22px;border-radius:10px;font-weight:600;"
                + "display:inline-block\">" + label + "</a></p>";
    }
}
