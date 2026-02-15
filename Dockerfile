# Build stage
FROM rust:1.76-slim-bookworm as builder

WORKDIR /app

# Installation des dépendances système nécessaires pour la compilation (OpenSSL, pkg-config)
RUN apt-get update && apt-get install -y pkg-config libssl-dev && rm -rf /var/lib/apt/lists/*

# Copie des fichiers de dépendances pour mettre en cache la compilation des crates
COPY Cargo.toml Cargo.lock ./
# Création d'un main.rs dummy pour compiler les dépendances
RUN mkdir src && echo "fn main() {}" > src/main.rs
RUN cargo build --release
RUN rm -f target/release/deps/sokoul*

# Copie du code source réel
COPY . .
RUN cargo build --release

# Runtime stage
FROM debian:bookworm-slim
WORKDIR /app
RUN apt-get update && apt-get install -y libssl-dev ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/target/release/sokoul /app/sokoul
CMD ["./sokoul"]