<?php
use Livewire\Component;
new class extends Component {
    public function mount(): void { abort_unless(auth('web')->user()?->puedePantalla('admin.receptores-yape'), 403); }
};
?>
<div><flux:button as="a" :href="route('admin.receptores-yape')">Abrir Receptores Yape</flux:button></div>
