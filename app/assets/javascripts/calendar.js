/* globals $ */

export function calendar() {

  // Initialize calendar
  var calendar = $("#calendar");
  initializeCalender();

  // Fetch events for calendars
  $.ajax({
    method: "GET",
    url: "/calendar/events",
    dataType: "json",
    timeout: 3000,
    success: function(response) {
      renderCalendarEvents(response);
    },
    error: function() {
      console.log("Failed to fetch calendar events");
    }
  });

  // Render empty calendar
  function initializeCalender() {
    calendar.fullCalendar({
      header: {
        left: "month,agendaWeek",
        center: "title"
      },
      buttonText: {
        month: "Month",
        agendaWeek: "Week",
        today: "Today"
      },
      nowIndicator: true,
      schedulerLicenseKey: "CC-Attribution-NonCommercial-NoDerivatives",
      dayClick: handleDayClick
    });
  }

  // Render events on calendar
  function renderCalendarEvents(events) {
    calendar.fullCalendar("renderEvents", events);
  }

  // Handler function for day click in month view
  function handleDayClick(date, event, view) {
    if (view.name == "month") {
      // Change to timeline view
      calendar.fullCalendar("gotoDate", date.format());
      calendar.fullCalendar("changeView", "timelineDay");
    }
  }

}
