package com.studentapp;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.PrintWriter;
import java.io.StringWriter;

public class NumberGuessServletTest {

    private NumberGuessServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private StringWriter body;

    @Before
    public void setUp() throws Exception {
        servlet = new NumberGuessServlet();
        request = Mockito.mock(HttpServletRequest.class);
        response = Mockito.mock(HttpServletResponse.class);
        session = Mockito.mock(HttpSession.class);

        body = new StringWriter();
        Mockito.when(response.getWriter()).thenReturn(new PrintWriter(body));
        Mockito.when(request.getSession()).thenReturn(session);
    }

    @Test
    public void testGuessTooLow() throws Exception {
        // target = 50, user guesses 1
        Mockito.when(session.getAttribute("target")).thenReturn(50);
        Mockito.when(request.getParameter("guess")).thenReturn("1");

        servlet.doPost(request, response);

        assertTrue(body.toString().contains("too low"));
    }

    @Test
    public void testGuessTooHigh() throws Exception {
        // target = 50, user guesses 100
        Mockito.when(session.getAttribute("target")).thenReturn(50);
        Mockito.when(request.getParameter("guess")).thenReturn("100");

        servlet.doPost(request, response);

        assertTrue(body.toString().contains("too high"));
    }

    @Test
    public void testCorrectGuess() throws Exception {
        // target = 42, user guesses 42
        Mockito.when(session.getAttribute("target")).thenReturn(42);
        Mockito.when(request.getParameter("guess")).thenReturn("42");

        servlet.doPost(request, response);

        assertTrue(body.toString().contains("Congratulations"));
        // optionally verify it resets a new target in session:
        Mockito.verify(session).setAttribute(Mockito.eq("target"), Mockito.anyInt());
    }
}
