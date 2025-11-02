import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgSseClient } from './ng-sse-client';

describe('NgSseClient', () => {
  let component: NgSseClient;
  let fixture: ComponentFixture<NgSseClient>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NgSseClient],
    }).compileComponents();

    fixture = TestBed.createComponent(NgSseClient);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
