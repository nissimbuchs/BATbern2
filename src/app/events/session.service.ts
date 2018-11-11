import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, tap, map } from 'rxjs/operators';

import { ISession } from './session';

@Injectable({
  providedIn: 'root'
})
export class SessionService {
  private sessionUrl = './api/sessions.json';

  constructor(private http: HttpClient) { }

  getSessions(): Observable<ISession[]> {
    return this.http.get<ISession[]>(this.sessionUrl).pipe();
  }

  getSessionsByBat(id: number): Observable<ISession[] | undefined> {
    return this.getSessions().pipe(
      map((sessions: ISession[]) => sessions.filter(p => p.bat === id))
    );
  }

  private handleError(err: HttpErrorResponse) {
    // in a real world app, we may send the server to some remote logging infrastructure
    // instead of just logging it to the console
    let errorMessage = '';
    if (err.error instanceof ErrorEvent) {
      // A client-side or network error occurred. Handle it accordingly.
      errorMessage = `An error occurred: ${err.error.message}`;
    } else {
      // The backend returned an unsuccessful response code.
      // The response body may contain clues as to what went wrong,
      errorMessage = `Server returned code: ${err.status}, error message is: ${err.message}`;
    }
    console.error(errorMessage);
    return throwError(errorMessage);
  }
}
