import { Component, OnInit } from '@angular/core';
import { ITopic } from '../topic';
import { TopicService } from '../topic.service';

@Component({
  selector: 'app-archiv',
  templateUrl: './archiv.component.html',
  styles: [`
    .leftCol {
      text-align: left;
    }`]
})
export class ArchivComponent implements OnInit {

  pageTitle = 'BAT Themen Liste';
  imageWidth = 50;
  imageMargin = 2;
  showImage = false;
  errorMessage = '';

  topics: ITopic[] = [];

  constructor(private topicService: TopicService) {
  }

  ngOnInit(): void {
    this.topicService.getTopics().subscribe(
      products => {
        this.topics = products;
      },
      error => this.errorMessage = <any>error
    );
  }
}
