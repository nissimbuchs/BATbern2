import { Component, OnInit, AfterViewInit, Input} from '@angular/core';
import { environment } from 'src/environments/environment';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styles: [`
    clr-header {
    position: fixed-top;
    top: 0pt;
    width: 100%;
    z-index:999;
  }
  clr-header .branding a .title {
    margin-left: 0.5rem;
    font-size: 1rem;
    text-overflow: unset;
  }
  @media screen and (max-width: 576px) {
    clr-header .branding {
      max-width: 12rem;
    }
  }
  `]
})
export class HeaderComponent implements OnInit {

  title: string = "BATbern";

  constructor() {
    if(environment.production) {
      this.title = "BATbern"
    }
   }

  ngOnInit(): void {
  }
}
