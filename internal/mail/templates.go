package mail

import (
	"fmt"
	"html"
)

// Paleta SmartPot para los correos (estilos en línea; los clientes de correo
// ignoran las hojas externas).
const (
	colorPrimary = "#067A52"
	colorDeep    = "#0B3D2B"
	colorBrand   = "#00B074"
	colorInk     = "#17261F"
	colorMuted   = "#5B6B63"
	colorSurface = "#F2F7F4"
)

type content struct {
	html string
	text string
}

func welcome(name, loginURL string) content {
	safeName := html.EscapeString(name)
	body := paragraph("Hello "+safeName+",") +
		paragraph("Your SmartPot account is ready. Create your first crop, real or virtual, and receive "+
			"AI assistant recommendations to keep it in its ideal range.") +
		button("Go to SmartPot", loginURL)
	text := "Hello " + name + ",\n\nYour SmartPot account is ready. Log in at: " + loginURL
	return content{html: layout("Welcome to SmartPot", body), text: text}
}

func passwordReset(name, resetURL string, minutes int64) content {
	safeName := html.EscapeString(name)
	body := paragraph("Hello "+safeName+",") +
		paragraph(fmt.Sprintf("We received a request to reset your password. The link expires in "+
			"%d minutes and can only be used once.", minutes)) +
		button("Reset password", resetURL) +
		small("If you did not request this change, please ignore this email: your password remains the same.")
	text := fmt.Sprintf("Hello %s,\n\nReset your password at: %s\nThe link expires in %d minutes. "+
		"If you did not request this change, please ignore this email.", name, resetURL, minutes)
	return content{html: layout("Reset your password", body), text: text}
}

func layout(title, body string) string {
	return "<!doctype html><html lang=\"en\"><body style=\"margin:0;background:" + colorSurface +
		";font-family:Segoe UI,Arial,sans-serif;color:" + colorInk + "\">" +
		"<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:24px 0\">" +
		"<tr><td align=\"center\"><table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" " +
		"style=\"background:#FFFFFF;border-radius:14px;overflow:hidden\">" +
		"<tr><td style=\"background:" + colorDeep + ";padding:22px 28px;border-bottom:4px solid " + colorBrand + "\">" +
		"<span style=\"color:#FFFFFF;font-size:20px;font-weight:700;letter-spacing:.3px\">Smart<span style=\"color:" +
		colorBrand + "\">Pot</span></span></td></tr>" +
		"<tr><td style=\"padding:28px\"><h1 style=\"margin:0 0 16px;font-size:22px;color:" + colorPrimary + "\">" +
		html.EscapeString(title) + "</h1>" + body + "</td></tr>" +
		"<tr><td style=\"padding:16px 28px;background:" + colorSurface + ";font-size:12px;color:" + colorMuted + "\">" +
		"SmartPot · Hydroponic crop monitoring and automation</td></tr>" +
		"</table></td></tr></table></body></html>"
}

func paragraph(text string) string {
	return "<p style=\"margin:0 0 14px;font-size:15px;line-height:1.55\">" + text + "</p>"
}

func small(text string) string {
	return "<p style=\"margin:18px 0 0;font-size:12px;color:" + colorMuted + "\">" + text + "</p>"
}

func button(label, url string) string {
	safeURL := html.EscapeString(url)
	return "<p style=\"margin:22px 0\"><a href=\"" + safeURL + "\" style=\"background:" + colorPrimary +
		";color:#FFFFFF;text-decoration:none;padding:12px 22px;border-radius:10px;font-weight:600;" +
		"display:inline-block\">" + label + "</a></p>"
}
