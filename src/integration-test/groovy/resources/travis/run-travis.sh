#!/usr/bin/env bash
if [[ $TEST_SUITE == "apollo" ]]; then
  travis_wait ./grailsw test-app
fi
if [[ $TEST_SUITE == "python-apollo" ]]; then
  set -ex
  cp src/integration-test/groovy/resources/travis/python-apollo.travis apollo-config.groovy
  ./grailsw run-app &
  git clone --single-branch --branch add_bulk_gff_loader --depth=1 https://github.com/galaxy-genome-annotation/python-apollo
  cd python-apollo
  pyenv global 3.6.3
  sed -i 's|8888|8080/apollo|' `pwd`/test-data/arrow.yml
  export ARROW_GLOBAL_CONFIG_PATH=`pwd`/test-data/arrow.yml
  virtualenv .venv
  . .venv/bin/activate
  pip install .
  pip install nose
  ./bootstrap_apollo.sh --nodocker
  python setup.py nosetests
  killall java || true
fi
