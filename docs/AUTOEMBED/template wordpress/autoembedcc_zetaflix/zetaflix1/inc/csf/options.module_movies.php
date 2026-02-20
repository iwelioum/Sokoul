<?php
/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('Movies'),
        'parent' => 'homepage',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id'      => 'moviestitle',
                'type'    => 'text',
                'title'   => __z('Module Title'),
                'default' => __z('Movies'),
                'subtitle' => __z('Add title to show')
            ),
            array(
                'id'    => 'moviesmodcontrol',
                'type'  => 'checkbox',
                'title' => __z('Module Control'),
                'subtitle'  => __z('Check to enable option.'),
                'options' => array(
                    'slider' => __z('Activate Module'),
                ),
                'default'=> array('slider')
            ),
            array(
                'id'    => 'moviesmdisplay',
                'type'  => 'radio',
                'title' => __z('Display As'),
                'subtitle' => __z('Select style of this module'),
                'options' => array(
                    'slider' => __z('Slider'),
                    'grid'  => __z('Grid')
                ),
                'default' => 'slider'
            ),			
            array(
                'id'      => 'moviesitems',
                'type'    => 'text',
                'title'   => __z('Slide Items'),
                'subtitle' => __z('Number of items to show'),
                'default' => '18',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:100px'
                ),
                'dependency' => array('moviesmdisplay','==','slider'),
            ),
            array(
                'id'      => 'moviesgitems',
                'type'    => 'text',
                'title'   => __z('Grid Items'),
                'subtitle' => __z('Number of items to show'),
                'default' => '18',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:100px'
                ),
                'dependency' => array('moviesmdisplay','==','grid'),
            ),
            array(
                'id'      => 'moviesmodorderby',
                'type'    => 'select',
                'title'   => __z('Order by'),
                'subtitle' => __z('Order items for this module'),
                'default' => 'date',
                'options' => array(
                    'date'     => __z('Post date'),
                    'title'    => __z('Post title'),
                    'modified' => __z('Last modified'),
                    'rand'     => __z('Random entry'),
                )
            ),
            array(
                'id'    => 'moviesmodorder',
                'type'  => 'radio',
                'title' => __z('Order'),
                'options' => array(
                    'DESC' => __z('Descending'),
                    'ASC'  => __z('Ascending')
                ),
                'dependency' => array('moviesmodorderby','!=','rand'),
                'default' => 'DESC'
            )
        )
    )
);
