#!/usr/bin/env bash

# i have no clue how exactly this works
# https://unix.stackexchange.com/questions/628810/bash-replace-multiple-lines-in-a-file-between-two-patterns-with-the-content-of-a
sed -i -e '
  /json-lines/,/<\/script>/!b
  //!d;/<\/script>/!b
  r ./data/episodes.jsonl
  N
' web/index.html