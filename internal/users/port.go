package users

import (
	"context"
	"errors"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

// Repository es el puerto de almacenamiento de las cuentas: lo que el servicio necesita de la capa de datos, sin
// atarse a un motor concreto. El adaptador de PostgreSQL (internal/storage/postgres) lo implementa; migrar de
// motor es escribir otro adaptador sin tocar este paquete (inversión de dependencias).
type Repository interface {
	FindByEmail(ctx context.Context, email string) (*User, error)
	ExistsByEmail(ctx context.Context, email string) (bool, error)
	FindByID(ctx context.Context, id string) (*User, error)
	Insert(ctx context.Context, u *User) error
	Update(ctx context.Context, u *User) error
	Delete(ctx context.Context, id primitive.ObjectID) error
}

// ErrDuplicateEmail lo devuelve el adaptador cuando el correo ya existe (viola el índice único). El servicio lo
// detecta con IsDuplicate sin conocer el motor de base de datos.
var ErrDuplicateEmail = errors.New("email is already registered")

// IsDuplicate indica si el error es por correo duplicado.
func IsDuplicate(err error) bool {
	return errors.Is(err, ErrDuplicateEmail)
}
