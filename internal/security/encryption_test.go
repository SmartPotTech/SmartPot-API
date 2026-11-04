package security

import (
	"bytes"
	"encoding/base64"
	"encoding/hex"
	"testing"
)

// zeroKeyB64 es una clave AES-256 de 32 bytes en cero, en Base64 estándar.
func zeroKeyB64() string {
	return base64.StdEncoding.EncodeToString(make([]byte, 32))
}

// TestDecryptStandardVector comprueba que el descifrado sigue el estándar
// AES-256-GCM (vector de prueba 14 de la especificación GCM: clave e IV en cero,
// 16 bytes de texto en cero). Si el formato coincide con el estándar, también
// descifra lo que produce AES/GCM/NoPadding en Java.
func TestDecryptStandardVector(t *testing.T) {
	iv, _ := hex.DecodeString("000000000000000000000000")
	ciphertext, _ := hex.DecodeString("cea7403d4d606b6e074ec5d3baf39d18")
	tag, _ := hex.DecodeString("d0d1c8a799996bf0265b98b5d48ab919")

	blob := append(append(append([]byte{}, iv...), ciphertext...), tag...)
	encoded := base64.RawURLEncoding.EncodeToString(blob)

	enc, err := NewEncryptor(zeroKeyB64())
	if err != nil {
		t.Fatalf("NewEncryptor: %v", err)
	}
	plain, err := enc.Decrypt(encoded)
	if err != nil {
		t.Fatalf("Decrypt of standard vector failed: %v", err)
	}
	if !bytes.Equal([]byte(plain), make([]byte, 16)) {
		t.Errorf("el texto descifrado no son 16 bytes en cero: %x", plain)
	}
}

func TestEncryptRoundTrip(t *testing.T) {
	enc, err := NewEncryptor("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=") // 32 bytes
	if err != nil {
		t.Fatalf("NewEncryptor: %v", err)
	}
	secret := "device-key-ÁÉÍ-123"
	cipherText, err := enc.Encrypt(secret)
	if err != nil {
		t.Fatalf("Encrypt: %v", err)
	}
	got, err := enc.Decrypt(cipherText)
	if err != nil {
		t.Fatalf("Decrypt: %v", err)
	}
	if got != secret {
		t.Errorf("round-trip = %q, se esperaba %q", got, secret)
	}
}

func TestEncryptUsesRandomIV(t *testing.T) {
	enc, _ := NewEncryptor(zeroKeyB64())
	a, _ := enc.Encrypt("mismo-texto")
	b, _ := enc.Encrypt("mismo-texto")
	if a == b {
		t.Error("dos cifrados del mismo texto no deberían coincidir (IV aleatorio)")
	}
}

func TestDecryptRejectsTampered(t *testing.T) {
	enc, _ := NewEncryptor(zeroKeyB64())
	cipherText, _ := enc.Encrypt("dato")
	tampered := cipherText[:len(cipherText)-2] + "AA"
	if _, err := enc.Decrypt(tampered); err == nil {
		t.Error("un texto cifrado manipulado debía fallar")
	}
	if _, err := enc.Decrypt("###"); err == nil {
		t.Error("an invalid Base64 should fail")
	}
}
