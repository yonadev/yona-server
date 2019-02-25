SHELL=/bin/bash
buildversion = $(shell cat version)

.PHONY: all
all: build

build:
	docker build --force-rm=true -t yonadev/yona-mariadb-liquibase-update:napi-build-$(buildversion) .

