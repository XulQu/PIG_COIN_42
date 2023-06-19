'use strict';

let roll = 0;
let diceImg = document.querySelector('.dice');
let current01 = 0;
let current02 = 0;


let oink = new Audio('./pigGrowl.mp3');
let hold = new Audio('./hold.mp3');
let diceSound = new Audio('./Dice.mp3');
let wrong = new Audio('./wrong.mp3');
oink.volume = 0.05;
hold.volume = 0.05;
diceSound.volume = 0.05;
wrong.volume = 0.05;

//refactored sections:
// buttons
let selectBtn = document.querySelector('.btn--roll');
let selectNew = document.querySelector('.btn--new');
let selectHold = document.querySelector('.btn--hold');

// Player select
let selectPlayer01 = document.querySelector('.player--0');
let selectPlayer02 = document.querySelector('.player--1');
//current score select
let selectCurrent01 = document.querySelector('#current--0');
let selectCurrent02 = document.querySelector('#current--1');
// player score select
let selectScore01 = document.querySelector('#score--0');
let selectScore02 = document.querySelector('#score--1');
// select name for winner
let selectName01 = document.querySelector('#name--0');
let selectName02 = document.querySelector('#name--1');
// contains player active
// let classContainsActive = classList.contains('player--active'); Im not sure if this will work, I wont test because it can casuse issues

// failed test
// if(document.querySelector('.player--0').classList.contains('player--active')){
//     console.log('player 1 is active')
// }else {
//     console.log('failed')
// }

// if(document.querySelector('.player--1').classList.contains('player--active')){
//     console.log('player 2 is active')
// }else {
//     console.log('failed')
// } // failed test


selectBtn.addEventListener('click', function () {

    roll = (Math.floor(Math.random() * 6 + 1));
    console.log(roll);

    

    if (roll === 1 && selectPlayer01.classList.contains('player--active')) {
        wrong.play();
        diceImg.src = 'dice-1.png';
        current01 = 0;
        selectCurrent01.textContent = current01;
        // Updates score to 0 and should pass to other player.
        selectScore01.textContent = current01;
        selectPlayer01.classList.remove('player--active');
        selectPlayer02.classList.add('player--active');
        // document.querySelector('.btn--roll').classList.remove('player01');
        // document.querySelector('.btn--roll').classList.add('player02');


    } else if (roll === 2 && selectPlayer01.classList.contains('player--active')) {
        diceSound.play();
        diceImg.src = 'dice-2.png'
        current01 = current01 + 2;
        selectCurrent01.textContent = current01;
    } else if (roll === 3 && selectPlayer01.classList.contains('player--active')) {
        diceSound.play();
        diceImg.src = 'dice-3.png'
        current01 = current01 + 3;
        selectCurrent01.textContent = current01;
    } else if (roll === 4 && selectPlayer01.classList.contains('player--active')) {
        diceSound.play();
        diceImg.src = 'dice-4.png';
        current01 = current01 + 4;
        selectCurrent01.textContent = current01;
    } else if (roll === 5 && selectPlayer01.classList.contains('player--active')) {
        diceSound.play();
        diceImg.src = 'dice-5.png'
        current01 = current01 + 5;
        selectCurrent01.textContent = current01;
    } else if (roll === 6 && selectPlayer01.classList.contains('player--active')) {
        diceSound.play();
        diceImg.src = 'dice-6.png'
        current01 = current01 + 6;
        selectCurrent01.textContent = current01;
    } else if (roll === 1 && selectPlayer02.classList.contains('player--active')) {
        wrong.play();
        diceImg.src = 'dice-1.png'
        current02 = 0;
        selectCurrent02.textContent = current02;
        // Updates score to 0 and should pass to other player.
        selectScore02.textContent = current02;
        selectPlayer02.classList.remove('player--active');
        selectPlayer01.classList.add('player--active');
        // document.querySelector('.btn--roll').classList.remove('player02');
        // document.querySelector('.btn--roll').classList.add('player01');

    } else if (roll === 2 && selectPlayer02.classList.contains('player--active')) {
        diceSound.play();
        diceImg.src = 'dice-2.png'
        current02 = current02 + 2;
        selectCurrent02.textContent = current02;
    } else if (roll === 3 && selectPlayer02.classList.contains('player--active')) {
        diceSound.play();
        diceImg.src = 'dice-3.png'
        current02 = current02 + 3;
        selectCurrent02.textContent = current02;
    } else if (roll === 4 && selectPlayer02.classList.contains('player--active')) {
        diceSound.play();
        diceImg.src = 'dice-4.png';
        current02 = current02 + 4;
        selectCurrent02.textContent = current02;
    } else if (roll === 5 && selectPlayer02.classList.contains('player--active')) {
        diceSound.play();
        diceImg.src = 'dice-5.png'
        current02 = current02 + 5;
        selectCurrent02.textContent = current02;
    } else if (roll === 6 && selectPlayer02.classList.contains('player--active')) {
        diceSound.play();
        diceImg.src = 'dice-6.png'
        current02 = current02 + 6;
        selectCurrent02.textContent = current02;
    }

})

selectHold.addEventListener('click', function () {

    hold.play();

    if (selectPlayer01.classList.contains('player--active')) {
        selectScore01.textContent = current01;
        selectPlayer01.classList.remove('player--active');
        selectPlayer02.classList.add('player--active');
    } else if (selectPlayer02.classList.contains('player--active')) {
        selectScore02.textContent = current02;
        selectPlayer02.classList.remove('player--active');
        selectPlayer01.classList.add('player--active');
    }

    if(selectScore01.textContent >= 42){
        selectPlayer01.classList.add('player--winner');
        selectName01.textContent = 'WINNER! 🐷';
        oink.play();
    }
    
    if(selectScore02.textContent >= 42){
        selectPlayer02.classList.add('player--winner');
        selectName02.textContent = 'WINNER! 🐷';
        oink.play();
    }
})

selectNew.addEventListener('click', function () {
    oink.pause();
    selectScore01.textContent = 0;
    selectScore02.textContent = 0;
    selectCurrent01.textContent = 0;
    selectCurrent02.textContent = 0;
    selectPlayer01.classList.remove('player--winner');
    selectPlayer02.classList.remove('player--winner');
    selectName01.textContent = 'PLAYER 1';
    selectName02.textContent = 'PLAYER 2';
    diceImg.src = 'dice-6.png'
    if(selectPlayer02.classList.contains('player--active')){
        selectPlayer02.classList.remove('player--active')
        selectPlayer01.classList.add('player--active')
    }

    
})


// WIN CON



