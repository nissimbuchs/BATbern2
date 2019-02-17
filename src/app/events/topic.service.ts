import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, tap, map } from 'rxjs/operators';

import { ITopic } from './topic';

@Injectable({
  providedIn: 'root'
})
export class TopicService {
  private topicUrl = './api/topics.json';

  constructor(private http: HttpClient) { }

  getTopics(): Observable<ITopic[]> {
    return this.http.get<ITopic[]>(this.topicUrl).pipe();
  }

  getTopicByBat(id: number): Observable<ITopic | undefined> {
    return this.getTopics().pipe(
      map((topics: ITopic[]) => topics.find(p => p.bat === id))
    );
  }

  getCurrentTopic(): Observable<ITopic | undefined> {
    return this.getTopics().pipe(
      map((topics: ITopic[]) => topics.find(p => p.bat === topics.length))
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
