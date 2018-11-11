import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  template:`
    <app-layout>
    <router-outlet></router-outlet>
    </app-layout>
  `,
  styles: []
})
export class AppComponent {
  title = 'Title of BATbern';
}
