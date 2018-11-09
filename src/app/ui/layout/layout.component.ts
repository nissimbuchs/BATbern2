import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-layout',
  template: `
  <clr-main-container>
    <app-header></app-header>
    <div class="content-container">
    <div class="content-area">
      <ng-content></ng-content>
    </div>
  </div>
  </clr-main-container>
  `,
  styles: []
})
export class LayoutComponent implements OnInit {

  constructor() { }

  ngOnInit() {
  }

}
