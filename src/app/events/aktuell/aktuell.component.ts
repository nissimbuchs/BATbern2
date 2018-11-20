import { Component, OnInit } from '@angular/core';
import { $ } from 'protractor';

@Component({
  selector: 'app-aktuell',
  templateUrl: './aktuell.component.html',
  styleUrls: ['./aktuell.component.css']
})
export class AktuellComponent implements OnInit {

  constructor() { }

  ngOnInit() {
  }

  onResize(event) {
    //console.log("resize "+event.target.innerHeight);
  }

  calculateHeight() {
    let style =+(window.innerHeight-60)+"px";
    //console.log("innerHeight: "+style);
    return style;
  }
}