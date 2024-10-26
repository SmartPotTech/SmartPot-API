@echo off

REM Construir la imagen
docker build --platform linux/amd64 -t sebastian190030/api-smarpot:latest .

REM Publicar la imagen
docker push sebastian190030/api-smarpot:latest