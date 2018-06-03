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
        center: "month,timelineDay"
      },
      schedulerLicenseKey: "CC-Attribution-NonCommercial-NoDerivatives"
    });
  }

  // Render events on calendar
  function renderCalendarEvents(events) {
    calendar.fullCalendar("renderEvents", events);
  }

}
