SHELL=/bin/bash
buildversion = $(shell cat version)

.PHONY: all
all: package

.PHONY: increment
increment:
	echo $$(( $(buildversion) + 1 )) > version

package:
	tar cvzf yona_smoothwall_parser_$(buildversion).tar.gz modules
