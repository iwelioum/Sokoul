<?php 

echo '<aside>';
if ( is_active_sidebar( 'sidebar-home' )):
dynamic_sidebar('sidebar-home');
else :
the_widget( 'ZT_Widget_mgenres' );
endif;
echo '<div class="clearfix"></div>';
echo '</aside>';

?>