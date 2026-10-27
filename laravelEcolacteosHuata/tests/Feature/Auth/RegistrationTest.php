<?php

test('self-registration is disabled: the register routes do not exist', function () {
    $this->get('/register')->assertNotFound();
    $this->post('/register')->assertNotFound();
});
