package com.studentapp;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class NumberGuessServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        out.println("<h1>Number Guessing Game</h1>");
        // contextPath keeps it working even if the app isn't deployed at root
        out.println("<form action='" + request.getContextPath() + "/guess' method='post'>");
        out.println("Guess a number between 1 and 100: <input type='text' name='guess' />");
        out.println("<input type='submit' value='Submit' />");
        out.println("</form>");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        // --- session-scoped target number (one per user) ---
        HttpSession session = request.getSession();
        Integer target = (Integer) session.getAttribute("target");
        if (target == null) {
            target = new Random().nextInt(100) + 1;
            session.setAttribute("target", target);
        }

        try {
            int guess = Integer.parseInt(request.getParameter("guess"));
            if (guess < target) {
                out.println("<h2>Your guess is too low for this type of game. Try again!</h2>");
            } else if (guess > target) {
                out.println("<h2>Your guess is too high. Try again!</h2>");
            } else {
                out.println("<h2>Congratulations! You guessed the number!</h2>");
                // reset for a new round
                session.setAttribute("target", new Random().nextInt(100) + 1);
            }
        } catch (NumberFormatException e) {
            out.println("<h2>Invalid input. Please enter a valid number.</h2>");
        }

        out.println("<a href='" + request.getContextPath() + "/guess'>Play Again</a>");
    }
}
