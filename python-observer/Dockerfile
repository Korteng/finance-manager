FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir --only-binary :all: -r requirements.txt

COPY app ./app

RUN useradd --system --no-create-home --uid 1000 appuser
USER appuser

EXPOSE 9100

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "9100"]
