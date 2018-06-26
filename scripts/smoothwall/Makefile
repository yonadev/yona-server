SHELL=/bin/bash
buildversion = $(shell cat version)

.PHONY: all
all: package

.PHONY: increment
increment:
	echo $$(( $(buildversion) + 1 )) > version

.PHONY: clean
clean:
	rm -f yona_smoothwall_parser_*.tar.gz

package:
	tar cvzf yona_smoothwall_parser_$(buildversion).tar.gz modules
