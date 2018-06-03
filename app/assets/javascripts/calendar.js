/* globals $ */

export function calendar() {

  // Fetch events for calendar
  $.ajax({
    method: "GET",
    url: "/calendar/events",
    dataType: "json",
    timeout: 3000,
    success: function(response) {
      renderCalendar(response);
    },
    error: function() {
      renderCalendar([]);
      console.log("Failed to fetch calendar events");
    }
  });

  // Render calendar with given events
  function renderCalendar(eventList) {
    var calendar = $("#calendar");
    calendar.fullCalendar({
      events: eventList
    });
  }

}
