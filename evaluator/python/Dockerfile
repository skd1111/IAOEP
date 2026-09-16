FROM python:3.12-alpine AS builder
WORKDIR /build

COPY pyproject.toml .
RUN pip install --no-cache-dir --upgrade pip build && \
    pip wheel --no-cache-dir --wheel-dir /wheels .

FROM python:3.12-alpine
WORKDIR /app

COPY --from=builder /wheels /wheels
RUN pip install --no-cache-dir /wheels/* && \
    rm -rf /wheels

COPY app ./app

EXPOSE 9000
ENV IAOEP_JUDGE_HOST=0.0.0.0
ENV IAOEP_JUDGE_PORT=9000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "9000"]
