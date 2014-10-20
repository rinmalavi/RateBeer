#!/bin/sh

LESS="less"
CSS="css"

files=$(ls $CSS/*.css 2> /dev/null | wc -l)
if [ "$files" != "0" ]
then
echo Cleaning old CSS files ...
rm $CSS/*.css
fi

echo Compiling LESS sources:


# for files in $LESS;
# do
# echo Processing: $files.less
# lessc $LESS/$files.less > $CSS/$files.css
# lessc --yui-compress $LESS/$files.less > $CSS/$files.min.css
# done

lessc less/bootstrap/bootstrap.less > css/bootstrap.css
uglifycss css/bootstrap.css > css/bootstrap.min.css

lessc less/style.less > css/style.css
uglifycss css/style.css > css/style.min.css

echo Done compiling LESS!