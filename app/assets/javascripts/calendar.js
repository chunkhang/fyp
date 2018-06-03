/* globals $ */

export function calendar() {

  // Initial calendar
  var calendar = $("#calendar");
  renderCalendar();

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
  function renderCalendar() {
    calendar.fullCalendar({
    });
  }

  // Render events on calendar
  function renderCalendarEvents(events) {
    calendar.fullCalendar("renderEvents", events);
  }

}
