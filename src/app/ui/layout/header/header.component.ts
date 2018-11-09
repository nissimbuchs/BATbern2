import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styles: [`
    clr-header {
    position: fixed;
    top: 0pt;
    width: 100%;
    z-index:999;
  }
  .title {
    margin1: 1rem;
  }
  `]
})
export class HeaderComponent implements OnInit {

  constructor() { }

  ngOnInit() {
  }

}
