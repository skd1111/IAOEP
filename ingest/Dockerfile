FROM golang:1.22-alpine AS builder
WORKDIR /src

# 缓存依赖
COPY go.mod go.sum* ./
RUN go mod download

# 构建
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /out/ingest ./cmd/ingest

# 运行镜像
FROM alpine:3.19
RUN apk add --no-cache ca-certificates tzdata
COPY --from=builder /out/ingest /usr/local/bin/ingest

EXPOSE 4317 4318 8080
ENTRYPOINT ["/usr/local/bin/ingest"]
