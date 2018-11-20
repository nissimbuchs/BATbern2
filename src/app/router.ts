import { Routes } from '@angular/router'
import { VereinComponent } from './events/verein/verein.component';
import { AktuellComponent } from './events/aktuell/aktuell.component';
import { AnmeldungComponent } from './events/anmeldung/anmeldung.component';
import { ThemenComponent } from './events/themen/themen.component';
import { ArchivComponent } from './events/archiv/archiv.component';
import { KontaktComponent } from './events/kontakt/kontakt.component';
import { ArchivDetailComponent } from './events/archiv-detail/archiv-detail.component';

export const appRoutes:Routes = [
   { path: '', component: AktuellComponent},
  // { path: 'anmeldung', component: AnmeldungComponent},
  // { path: 'themen', component: ThemenComponent},
  { path: 'event', component: ArchivComponent},
  { path: 'event/:bat', component: ArchivDetailComponent},
  // { path: 'verein', component: VereinComponent},
  // { path: 'kontakt', component: KontaktComponent},
  // { path: '', redirectTo: '/aktuell', pathMatch: 'full'},
  // { path: '**', redirectTo: '/aktuell'}
]