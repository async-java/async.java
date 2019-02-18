'use strict';

const path = require('path');
const fs = require('fs');

const replaceMap = {
  baseURL: 'https://async-java.github.io/org/ores/async/Asyncc.html'
};


// var regex = /\[(.*?)\]/;
// var strToMatch = "This is a test string [more or less] [more or less]";
// var matched = regex.exec(strToMatch);
// // var matched =''
// console.log(matched[2]);


const info = String(fs.readFileSync(__dirname + '/../readme.pre.md'));
const outputFile = path.resolve(__dirname + '/../readme.md');
const strm = fs.createWriteStream(outputFile);

let prevThree = [];
let isVarCapture = false;
let currentVarName = '';


for (let v of info) {
  
  if (isVarCapture === true && v === '}') {
    isVarCapture = false;
    let r = replaceMap[currentVarName];
    if (!r) {
      throw new Error('Variable was not in the map: ' + currentVarName);
    }
    strm.write(r);
    currentVarName = '';
    continue;
  }
  
  // console.log(prevThree);
  if (prevThree.join('') === '@={') {
    prevThree = [];
    if (isVarCapture === true) {
      throw new Error('Bad characters within variable interpolation.');
    }

    isVarCapture = true;
  }
  
  if(prevThree.length > 2){
    strm.write(prevThree.shift());
  }
  
  if (!isVarCapture) {
    prevThree.push(v);
    continue;
  }
  
  currentVarName += v;
  
}


strm.write('\n');

