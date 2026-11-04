package channels

import (
	"context"
	"errors"
	"log/slog"
)

// Sender entrega un mensaje al chat del dueño y a los chats compartidos. Registra
// entregas y fallos: el vínculo del dueño se pausa tras varios fallos y un chat
// que bloqueó al bot deja de recibir avisos.
type Sender struct {
	channels     *Service
	cropChannels *CropChannelService
}

// NewSender crea el emisor.
func NewSender(channels *Service, cropChannels *CropChannelService) *Sender {
	return &Sender{channels: channels, cropChannels: cropChannels}
}

// ToOwner envía el mensaje al chat del dueño.
func (s *Sender) ToOwner(ctx context.Context, channel NotificationChannel, link *ChannelLink, message ChannelMessage) {
	if err := channel.Send(link.Address, message); err != nil {
		slog.Warn("notification could not be delivered", "type", link.Type, "err", err)
		s.channels.Failed(ctx, link, permanent(err))
		return
	}
	s.channels.Delivered(ctx, link)
}

// ToRecipients envía el mensaje a los chats con los que se comparte el cultivo.
func (s *Sender) ToRecipients(ctx context.Context, channel NotificationChannel, settings *CropChannel, message ChannelMessage) {
	recipients := make([]Recipient, len(settings.Recipients))
	copy(recipients, settings.Recipients)
	for _, r := range recipients {
		if err := channel.Send(r.Address, message); err != nil {
			slog.Warn("shared alert could not be delivered", "type", settings.Type, "err", err)
			if permanent(err) {
				s.cropChannels.RemoveAddress(ctx, settings, r.Address)
			}
		}
	}
}

func permanent(err error) bool {
	var de *DeliveryError
	return errors.As(err, &de) && de.Permanent
}
