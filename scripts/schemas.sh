#!/bin/sh

LOCATION=`pwd`

FILES="$LOCATION/manual/src/main/paradox/schemas/*.ditaa"
for f in $FILES
do
  FILE=`echo $f | sed 's/ditaa/png/g' | sed 's/schemas/imgs/g'`
  echo "java -jar $LOCATION/scripts/tools/ditaa.jar -E -S -o $f $FILE"
	java -jar $LOCATION/scripts/tools/ditaa.jar -E -S -o $f $FILE
done