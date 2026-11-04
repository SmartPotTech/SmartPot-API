package telegram

import (
	"context"
	"strconv"
	"strings"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/channels"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
)

const helpText = "I am the <b>SmartPot</b> assistant 🌱\n" +
	"I notify you here when a crop needs attention, when a crop disconnects, or when the " +
	"assistant acts autonomously.\n\n" +
	"/status — how your crops and shared crops are doing\n" +
	"/unlink — stop receiving alerts in this chat\n" +
	"/help — this message\n\n" +
	"To link this chat go to SmartPot › Profile › Applications and tap «Link Telegram». If someone " +
	"shares a crop with you, open the link they send you."

const notLinked = "This chat is not linked yet. Go to SmartPot › Profile › Applications and tap «Link Telegram»."

// Bot atiende la conversación con el bot (solo chats privados).
type Bot struct {
	channels     *channels.Service
	cropChannels *channels.CropChannelService
	crops        *crops.Service
	client       *Client
}

// NewBot crea el bot.
func NewBot(ch *channels.Service, cc *channels.CropChannelService, cropsSvc *crops.Service, client *Client) *Bot {
	return &Bot{channels: ch, cropChannels: cc, crops: cropsSvc, client: client}
}

// Handle atiende un mensaje entrante.
func (b *Bot) Handle(update Update) {
	msg := update.Message
	if msg == nil || msg.Chat == nil || msg.Text == "" {
		return
	}
	chatID := strconv.FormatInt(msg.Chat.ID, 10)
	if msg.Chat.Type != "private" {
		b.reply(chatID, "Message me in a private chat to link your crops.")
		return
	}
	parts := strings.SplitN(strings.TrimSpace(msg.Text), " ", 2)
	command := strings.ToLower(parts[0])
	if i := strings.IndexByte(command, '@'); i >= 0 {
		command = command[:i]
	}
	argument := ""
	if len(parts) > 1 {
		argument = strings.TrimSpace(parts[1])
	}
	switch command {
	case "/start":
		b.start(chatID, argument, msg)
	case "/status", "/estado":
		b.reply(chatID, b.status(chatID))
	case "/unlink", "/desvincular":
		b.reply(chatID, b.unlink(chatID))
	default:
		b.reply(chatID, helpText)
	}
}

func (b *Bot) start(chatID, code string, msg *Message) {
	if code == "" {
		b.reply(chatID, helpText)
		return
	}
	ctx := context.Background()
	name := ""
	if msg.From != nil && msg.From.FirstName != "" {
		name = msg.From.FirstName
	} else if msg.Chat != nil {
		name = msg.Chat.FirstName
	}
	display := name
	if msg.Chat != nil && msg.Chat.Username != "" {
		display = "@" + msg.Chat.Username
	}
	greeting := ""
	if name != "" {
		greeting = ", " + escape(name)
	}
	if channels.IsShareCode(code) {
		crop, ok := b.cropChannels.AcceptShare(ctx, channels.Telegram, code, chatID, display)
		if ok && crop != nil {
			b.reply(chatID, "All set"+greeting+"! From now on you will receive alerts here for «"+escape(crop.Name)+
				"». Type /status to view it and /unlink to stop receiving alerts.")
		} else {
			b.reply(chatID, "The link expired, has already been used, or the crop is shared with too many chats. Request a new one.")
		}
		return
	}
	link, ok := b.channels.CompleteLink(ctx, channels.Telegram, code, chatID, display)
	if ok && link != nil {
		b.reply(chatID, "All set"+greeting+"! This chat is now linked to your SmartPot account. I will notify you of "+
			"alerts, disconnections, and assistant actions; you can customize them in your profile.\n\nType /status to view your crops.")
	} else {
		b.reply(chatID, "The link expired or has already been used. Generate a new one from SmartPot › Profile › Notifications.")
	}
}

func (b *Bot) unlink(chatID string) string {
	ctx := context.Background()
	linked, _ := b.channels.UnlinkAddress(ctx, channels.Telegram, chatID)
	shared, _ := b.cropChannels.Leave(ctx, channels.Telegram, chatID)
	if !linked && shared == 0 {
		return "This chat was not linked to any account and was not receiving shared alerts."
	}
	return "Done: you will no longer receive alerts in this chat. You can re-link it anytime from your profile."
}

func (b *Bot) status(chatID string) string {
	ctx := context.Background()
	link, _ := b.channels.FindByAddress(ctx, channels.Telegram, chatID)
	shared, _ := b.cropChannels.SharedWith(ctx, channels.Telegram, chatID)
	if link == nil && len(shared) == 0 {
		return notLinked
	}
	var own []crops.Crop
	if link != nil {
		own, _ = b.crops.List(ctx, link.UserID.Hex())
	}
	if len(own) == 0 && len(shared) == 0 {
		return "You do not have any crops yet. Create your first one in the app."
	}
	var text strings.Builder
	if len(own) > 0 {
		text.WriteString("🌱 <b>Your crops</b>\n")
		describe(&text, own)
	}
	if len(shared) > 0 {
		if text.Len() > 0 {
			text.WriteString("\n\n")
		}
		text.WriteString("🤝 <b>Shared with you</b>\n")
		describe(&text, shared)
	}
	return text.String()
}

func describe(text *strings.Builder, list []crops.Crop) {
	for i := range list {
		crop := &list[i]
		online := crop.Device != nil && crop.Device.Online
		text.WriteString("\n• <b>" + escape(crop.Name) + "</b>")
		if crop.IsVirtual() {
			text.WriteString(" (virtual)")
		}
		if online {
			text.WriteString(" — 🟢 online")
		} else {
			text.WriteString(" — ⚪ offline")
		}
		if crop.Health != nil {
			text.WriteString(" · " + escape(crop.Health.Label) + " (" + strconv.Itoa(int(round(crop.Health.Index))) + "/100)")
		}
		if crop.AutomationEnabled {
			text.WriteString(" · 🤖 auto")
		}
	}
}

func (b *Bot) reply(chatID, html string) {
	_ = b.client.SendMessage(chatID, html, "", "")
}

func round(v float64) float64 {
	return float64(int64(v + 0.5))
}
