package auth

import (
	"context"
	"time"
)

// TokenRepository es el puerto de almacenamiento de los tokens de recuperación de contraseña. Lo implementa el
// adaptador de PostgreSQL (internal/storage/postgres).
type TokenRepository interface {
	FindByTokenHash(ctx context.Context, hash string) (*PasswordResetToken, error)
	ConsumeByTokenHash(ctx context.Context, hash string, now time.Time) (*PasswordResetToken, error)
	Insert(ctx context.Context, t *PasswordResetToken) error
	DeleteByUserID(ctx context.Context, userID string) error
	// DeleteExpired borra los tokens vencidos (reemplaza al índice TTL anterior); lo llama la tarea de limpieza.
	DeleteExpired(ctx context.Context, now time.Time) (int64, error)
}
