#!/usr/bin/env bash

replace_references_in_schema() {
  schema_file=${1:-}
  reference_to_replace=${2:-}

  if [ -f "${schema_file}.original" ]
  then
    rm ${schema_file}.original
  fi

  # Hack to fix references in the schema
  sed -i .original \
    "s/\(.*ref.*\)\(${reference_to_replace}\)\(.*\)/\1\2.json\3/g" \
    ${schema_file}
}

replace_changed_schema_file() {
  schema_file=${1:-}

  rm ${schema_file}
  mv ${schema_file}.original ${schema_file}
}

loans_schema_file="ramls/schema/loans.json"

npm install

replace_references_in_schema ${loans_schema_file} loan

./node_modules/.bin/eslint ramls/circulation.raml

replace_changed_schema_file ${loans_schema_file}
