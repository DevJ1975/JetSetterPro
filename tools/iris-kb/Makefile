PY ?= python3
export PYTHONPATH := src

.PHONY: ingest chunk build eval verify-parity all clean

ingest:
	$(PY) -m iris_kb.cli ingest

chunk:
	$(PY) -m iris_kb.cli chunk

build:
	$(PY) -m iris_kb.cli build

eval:
	$(PY) -m iris_kb.cli eval

verify-parity:
	$(PY) -m iris_kb.cli verify-parity

all:
	$(PY) -m iris_kb.cli all

clean:
	rm -rf dist
