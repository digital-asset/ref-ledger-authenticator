# -*- makefile -*-

# paths
src_path      := build.sbt $(shell find src/main -type f -name "*.scala")
daml_src_path := $(shell find daml/ -type f -name "*.daml")

# targets
runner := target/pack/bin/authentication-service # see dockerfile
dar    := .daml/dist/authentication-service-3.0.0.dar
bundled-dar := src/main/resources/daml.dar

.PHONY: all
all: build

.PHONY: build
build: $(runner) $(dar)

$(dar): $(daml_src_path)
	daml build # see daml.yaml for more parameters
	daml codegen scala

$(runner): $(src_path) $(dar) $(bundled-dar)
	sbt pack

.PHONY: test
test: scala-test

.PHONY: run
run: $(runner)
	sh $<

.PHONY: scala-test
scala-test:
	sbt test

$(bundled-dar): $(dar)
	cp $< $@

.PHONY: assembly
assembly: $(dar) $(bundled-dar)
	sbt 'set test in assembly := {}' clean assembly

.PHONY: clean
clean:
	rm -fr scala-codegen/src
	rm -f $(dar)
	rm -f $(bundled-dar)
	sbt clean
