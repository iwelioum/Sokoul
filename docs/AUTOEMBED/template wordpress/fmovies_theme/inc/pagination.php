<?php
/**
 * Theme pagination
 *
 * @package fmovie
 */
 
//pagination
function fmovie_pagination($pages = '', $range = 2)
{  
     $showitems = ($range * 2)+1;  
     global $paged;
     if(empty($paged)) $paged = 1;
     if($pages == '')
     {
         global $wp_query;
         $pages = $wp_query->max_num_pages;
         if(!$pages)
         {
             $pages = 1;
         }
     }   
     if(1 != $pages)
     {
         echo "<ul class='pagination'>";
		 if($paged > 3 && $showitems < $pages) echo "<li><a style='background:none;' href='".get_pagenum_link(1)."'>" . esc_html__( 'First', 'fmovie' ) . "</a></li>";
         if($paged > 1 && $showitems < $pages) echo "<li><a class='prev' href='".get_pagenum_link($paged - 1)."'>←</a></li>";
         
         for ($i=1; $i <= $pages; $i++)
         {
             if (1 != $pages &&( !($i >= $paged+$range+1 || $i <= $paged-$range-1) || $pages <= $showitems ))
             {
                 echo ($paged == $i)? "<li class='active'><a href='".get_pagenum_link($i)."'  >".$i."</a></li>":"<li><a href='".get_pagenum_link($i)."'  >".$i."</a></li>";
             }
         }
         if ($paged < $pages && $showitems < $pages) echo "<li><a class='next' href='".get_pagenum_link($paged + 1)."'>→</a></li>";  
         if($paged != $pages) echo "<li><a style='background:none;' href='".get_pagenum_link($pages)."'>" . esc_html__( 'Last', 'fmovie' ) . "</a></li>";
         echo "</ul>\n";
     }
}
