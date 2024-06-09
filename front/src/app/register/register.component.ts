import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { ChatService } from '../services/chat/chat.service';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss']
})
export class RegisterComponent {

  public login: string = '';
  public password: string = '';
  public errorMessage: string = '';

  constructor(private chatService: ChatService, private router: Router) { }

  register(): void {
    this.chatService.register({ login: this.login, password: this.password }).subscribe(response => {
      if ('"error"' === JSON.stringify(response.status)) {
        this.errorMessage = 'Registration failed: ' + response.message;
      } else {
        this.router.navigate(['/login']);
      }
    }, error => {
      this.errorMessage = 'Registration failed: ' + error.message;
    });
  }
}
