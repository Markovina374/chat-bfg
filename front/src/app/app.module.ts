import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';  // Добавьте это
import { RouterModule, Routes } from '@angular/router';  // Добавьте это

import { AppComponent } from './app.component';
import { LoginComponent } from './login/login.component';
import { RegisterComponent } from './register/register.component';
import { ChatService } from './services/chat/chat.service';
import {ChatComponent} from "./chat/chat.component";

const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'chat', component: ChatComponent }
];

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    RegisterComponent,
    ChatComponent
  ],
  imports: [
    BrowserModule,
    FormsModule,  // Добавьте это
    RouterModule.forRoot(routes)  // Добавьте это
  ],
  providers: [ChatService],
  bootstrap: [AppComponent]
})
export class AppModule { }
