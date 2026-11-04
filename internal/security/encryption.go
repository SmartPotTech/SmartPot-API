// Package security reúne el cifrado, los tokens JWT, las contraseñas y los
// middleware de seguridad (CORS, límite de peticiones y autenticación),
// equivalentes a la cadena de seguridad de la versión en Java.
package security

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"strings"
)

// Encryptor cifra con AES-256-GCM e IV aleatorio por operación. El resultado es
// Base64 URL-safe (sin relleno) de IV ‖ texto cifrado+etiqueta, idéntico al
// formato que produce y consume la versión en Java.
type Encryptor struct {
	key []byte
}

// NewEncryptor valida la clave (Base64 de 32 bytes) y crea el cifrador.
func NewEncryptor(aesKeyBase64 string) (*Encryptor, error) {
	key, err := base64.StdEncoding.DecodeString(aesKeyBase64)
	if err != nil || len(key) != 32 {
		return nil, errors.New("SMARTPOT_AES_KEY must be 32-byte Base64 (AES-256)")
	}
	return &Encryptor{key: key}, nil
}

// Encrypt devuelve el texto cifrado en Base64 URL-safe sin relleno.
func (e *Encryptor) Encrypt(plainText string) (string, error) {
	gcm, err := e.gcm()
	if err != nil {
		return "", err
	}
	iv := make([]byte, gcm.NonceSize())
	if _, err := rand.Read(iv); err != nil {
		return "", err
	}
	sealed := gcm.Seal(iv, iv, []byte(plainText), nil)
	return base64.RawURLEncoding.EncodeToString(sealed), nil
}

// Decrypt descifra el texto producido por Encrypt (o por la versión en Java).
func (e *Encryptor) Decrypt(cipherText string) (string, error) {
	// El codificador de Java no pone relleno; aceptamos también valores con relleno.
	decoded, err := base64.RawURLEncoding.DecodeString(strings.TrimRight(cipherText, "="))
	if err != nil {
		return "", errors.New("could not decrypt data")
	}
	gcm, err := e.gcm()
	if err != nil {
		return "", err
	}
	if len(decoded) <= gcm.NonceSize() {
		return "", errors.New("texto cifrado incompleto")
	}
	iv := decoded[:gcm.NonceSize()]
	plain, err := gcm.Open(nil, iv, decoded[gcm.NonceSize():], nil)
	if err != nil {
		return "", errors.New("could not decrypt data")
	}
	return string(plain), nil
}

func (e *Encryptor) gcm() (cipher.AEAD, error) {
	block, err := aes.NewCipher(e.key)
	if err != nil {
		return nil, err
	}
	return cipher.NewGCM(block)
}
